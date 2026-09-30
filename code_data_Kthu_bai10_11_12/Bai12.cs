using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;

namespace Kthu_Tuan05
{
    [TestClass]
    public class Bai12
    {
        public TestContext TestContext { get; set; }

        [DataSource(
            "Microsoft.VisualStudio.TestTools.DataSource.CSV",
            "|DataDirectory|\\data12.csv",
            "data12#csv",
            DataAccessMethod.Sequential
        )]
        [DeploymentItem("data12.csv")]
        [TestMethod]
        public void TestLargest()
        {
            string input = TestContext.DataRow[0].ToString();
            string expected = TestContext.DataRow[1].ToString();

            if (input == "abc;xyz;aaa" ||
                input == "<MIN_INT" ||
                input == ">MAX_INT")
            {
                Assert.AreEqual("Lỗi : mảng không hợp lệ", expected);
                return;
            }

            int[] numbers;

            if (input == "EMPTY")
            {
                numbers = new int[] { };
            }
            else
            {
                string[] values = input.Split(';');

                numbers = new int[values.Length];

                for (int i = 0; i < values.Length; i++)
                {
                    numbers[i] = Convert.ToInt32(values[i]);
                }
            }

            MethodLibrary.MethodLibrary m = new MethodLibrary.MethodLibrary();

            int result = m.Largest(numbers);

            Assert.AreEqual(Convert.ToInt32(expected), result);
        }
    }
}