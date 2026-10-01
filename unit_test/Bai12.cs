using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;

namespace N7_TestHopDen
{
    [TestClass]
    public class bai12
    {
        public TestContext TestContext { get; set; }

        [TestMethod]
        [DataSource(
    "Microsoft.VisualStudio.TestTools.DataSource.CSV",
    "|DataDirectory|\\Bai12.csv",
    "Bai12#csv",
     DataAccessMethod.Sequential)]
        [DeploymentItem("data_csv\\Bai12.csv")]
        public void TestQuickSort()
        {
            string listValue = TestContext.DataRow[0].ToString();
            string leftValue = TestContext.DataRow[1].ToString();
            string rightValue = TestContext.DataRow[2].ToString();
            string expected = TestContext.DataRow[3].ToString();

            if (expected == "Lỗi kiểu dữ liệu")
            {
                Assert.AreEqual("Lỗi kiểu dữ liệu", expected);
                return;
            }

            if (expected == "Lỗi / Exception")
            {
                Assert.ThrowsException<Exception>(() =>
                {
                    string[] values = listValue.Split(';');

                    int[] list = new int[values.Length];

                    for (int i = 0; i < values.Length; i++)
                    {
                        list[i] = Convert.ToInt32(values[i]);
                    }

                    int leftIndexException = Convert.ToInt32(leftValue);
                    int rightIndexException = Convert.ToInt32(rightValue);

                    MethodLibrary.MethodLibrary m =
                        new MethodLibrary.MethodLibrary();

                    m.QuickSort(list, leftIndexException, rightIndexException);
                });

                return;
            }

            int leftIndex = Convert.ToInt32(leftValue);
            int rightIndex = Convert.ToInt32(rightValue);

            int[] numbers;

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

            MethodLibrary.MethodLibrary method = new MethodLibrary.MethodLibrary();

            method.QuickSort(numbers, leftIndex, rightIndex);

            string actual = string.Join(";", numbers);

            Assert.AreEqual(expected, actual);
        }
    }
}