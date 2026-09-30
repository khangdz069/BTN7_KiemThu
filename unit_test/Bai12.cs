using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;

namespace N7_btn
{
    [TestClass]
    public class Bai12
    {
        public TestContext TestContext { get; set; }

        [DataSource(
            "Microsoft.VisualStudio.TestTools.DataSource.CSV",
            "|DataDirectory|\\data_csv\\Bai12.csv",
            "Bai12#csv",
            DataAccessMethod.Sequential)]
        [DeploymentItem("data_csv\\Bai12.csv")]
        [TestMethod]
        public void TestQuickSort()
        {
            string expected = Convert.ToString(TestContext.DataRow[3]);
            bool mongdoi_exception = Convert.ToBoolean(TestContext.DataRow[4]);
            int[] numbers = null;

            try
            {
                string listValue = Convert.ToString(TestContext.DataRow[0]);
                int leftIndex = Convert.ToInt32(TestContext.DataRow[1]);
                int rightIndex = Convert.ToInt32(TestContext.DataRow[2]);

                if (string.IsNullOrEmpty(listValue))
                {
                    numbers = new int[0];
                }
                else
                {
                    string[] values = listValue.Split(';');
                    numbers = new int[values.Length];

                    for (int i = 0; i < values.Length; i++)
                    {
                        numbers[i] = Convert.ToInt32(values[i]);
                    }
                }

                MethodLibrary.MethodLibrary method =
                    new MethodLibrary.MethodLibrary();

                method.QuickSort(numbers, leftIndex, rightIndex);
            }
            catch (Exception)
            {
                Assert.IsTrue(mongdoi_exception);
                return;
            }

            Assert.IsFalse(mongdoi_exception);
            string actual = string.Join(";", numbers);
            Assert.AreEqual(expected, actual);
        }
    }
}
